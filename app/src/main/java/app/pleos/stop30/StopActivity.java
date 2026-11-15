package app.pleos.stop30;

import android.app.Activity;
import android.car.Car;
import android.car.VehicleGear;
import android.car.VehiclePropertyIds;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.constants.WaypointIndex;
import ai.pleos.playground.navi.data.CurrentLocationInfo;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.data.RequestWaypointInfo;
import ai.pleos.playground.navi.data.RouteInfo;
import ai.pleos.playground.navi.data.RouteStartInfo;
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest;
import ai.pleos.playground.navi.data.WaypointInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import kotlin.Unit;

public final class StopActivity extends Activity {
    static final String TAG = "PleosStop30";
    private static final String DESTINATION = "분당구청";
    private static final double DEST_LAT = 37.382631;
    private static final double DEST_LON = 127.118780;
    private static final double ORIGIN_LAT = 37.4127212;
    private static final double ORIGIN_LON = 127.094681;
    private static final StopPoint DETOUR = new StopPoint("시험용 우회 지점", "", 37.423876, 127.094661, "");

    private enum Action { START, DETOUR, RESTORE, CHECK_ADD, ADD, SCHEDULE }
    private enum Cause { BASE, ROUTE, SCHEDULE }

    private record StopPoint(String name, String id, double latitude, double longitude, String address) {
        RequestWaypointInfo request(WaypointIndex index) {
            return new RequestWaypointInfo(latitude, longitude, name, index, id, address, "0", "");
        }

        boolean matches(WaypointInfo item) {
            return item != null && name.equals(item.getName()) && (id.isEmpty() || id.equals(item.getPoiId()));
        }
    }

    private static final class RouteRead {
        final Action action;
        final List<StopPoint> stops;
        final StopScenario.Venue selected;
        final boolean changesRoute;
        boolean started;
        DestinationInfo destination;
        List<WaypointInfo> waypoints;

        RouteRead(Action action, List<StopPoint> stops, StopScenario.Venue selected) {
            this.action = action;
            this.stops = stops;
            this.selected = selected;
            changesRoute = action != Action.CHECK_ADD && action != Action.SCHEDULE;
            started = !changesRoute;
        }

        String firstName() { return stops.isEmpty() ? DESTINATION : stops.get(0).name(); }

        boolean matches(List<WaypointInfo> items) {
            if (items == null || items.size() != stops.size()) return false;
            for (int i = 0; i < stops.size(); i++) {
                if (!stops.get(i).matches(items.get(i)) || items.get(i).getRemainTime() <= 0) return false;
            }
            return true;
        }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private StopScreen screen;
    private Car car;
    private CarPropertyManager vehicle;
    private CarUxRestrictionsManager restrictions;
    private NaviHelper nav;
    private ExampleSpeech speech;
    private boolean destroyed;
    private boolean parked;
    private boolean navReady;
    private boolean locationPending;
    private boolean detour;
    private boolean scheduleChanged;
    private boolean added;
    private RouteRead pending;
    private StopScenario.Constraints constraints;
    private StopScenario.Result prediction;
    private StopScenario.Estimate receipt;
    private Cause cause = Cause.BASE;
    private int reference;
    private int eta;
    private int previousEta;
    private String transcript = "";
    private String message = "차량 상태와 순정 내비를 확인하고 있어요.";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        bindScreen();
        connectVehicle();
        connectNavigation();
        ui.post(pollGear);
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        bindScreen();
    }

    private void bindScreen() {
        screen = new StopScreen(this);
        setContentView(screen.root);
        screen.primary.setOnClickListener(view -> {
            if (prediction == null) startExample();
            else checkForAdd();
        });
        screen.reset.setOnClickListener(view -> startExample());
        screen.route.setOnClickListener(view -> changeRoute());
        screen.schedule.setOnClickListener(view -> verifySchedule());
        render();
    }

    private void connectVehicle() {
        car = Car.createCar(this, ui, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, (connection, ready) -> {
            if (destroyed) return;
            vehicle = ready ? (CarPropertyManager) connection.getCarManager(Car.PROPERTY_SERVICE) : null;
            restrictions = ready ? (CarUxRestrictionsManager) connection.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) : null;
            updatePark();
        });
    }

    private void connectNavigation() {
        nav = new NaviHelper(getApplicationContext());
        nav.initialize(() -> {
            ui.post(() -> {
                if (destroyed) return;
                nav.addListener(events);
                navReady = true;
                message = "예시 음성으로 분당구청 경로를 불러오세요. 순정 내비가 열리면 하단 앱 아이콘으로 돌아오세요.";
                render();
            });
            return Unit.INSTANCE;
        }, error -> {
            Log.w(TAG, "NAV_INIT", error);
            ui.post(() -> {
                if (destroyed) return;
                message = "순정 내비를 연결한 뒤 앱을 다시 열어 주세요.";
                render();
            });
            return Unit.INSTANCE;
        });
    }

    @Override protected void onResume() {
        super.onResume();
        updatePark();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        ui.removeCallbacksAndMessages(null);
        if (speech != null) speech.cancel();
        if (nav != null) {
            if (navReady) nav.removeListener(events);
            nav.release();
        }
        if (car != null) car.disconnect();
        super.onDestroy();
    }

    private final Runnable pollGear = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            updatePark();
            ui.postDelayed(this, 450);
        }
    };

    @SuppressWarnings("deprecation")
    private boolean readPark() {
        if (vehicle == null || restrictions == null) return false;
        try {
            var gear = vehicle.getProperty(Integer.class, VehiclePropertyIds.GEAR_SELECTION, 0);
            var limits = restrictions.getCurrentCarUxRestrictions();
            return gear != null && gear.getStatus() == CarPropertyValue.STATUS_AVAILABLE
                    && Integer.valueOf(VehicleGear.GEAR_PARK).equals(gear.getValue())
                    && limits != null && !limits.isRequiresDistractionOptimization();
        } catch (RuntimeException error) {
            return false;
        }
    }

    private void updatePark() {
        boolean value = readPark();
        if (value == parked) return;
        parked = value;
        Log.i(TAG, "PARKED " + parked);
        if (!parked && busy()) {
            if (speech != null) speech.cancel();
            if (pending != null && pending.changesRoute) clearPrediction();
            speech = null;
            locationPending = false;
            pending = null;
            message = "조작을 멈췄어요. P단에서 다시 시도하세요.";
        }
        render();
    }

    private boolean busy() { return speech != null || locationPending || pending != null; }

    private boolean canOperate() {
        updatePark();
        return !destroyed && parked && navReady && !busy();
    }

    private void startExample() {
        if (!canOperate()) return;
        clearPrediction();
        constraints = null;
        transcript = "";
        cause = Cause.BASE;
        try {
            speech = new ExampleSpeech(getApplicationContext(), ui, this::finishSpeech);
            message = "예시 음성을 인식하고 있어요.";
            speech.start();
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "EXAMPLE_START", error);
            if (speech != null) speech.cancel();
            speech = null;
            message = "예시 음성을 시작하지 못했어요. 다시 시도하세요.";
        }
        render();
    }

    private void finishSpeech(String text) {
        if (speech == null || destroyed) return;
        speech = null;
        updatePark();
        if (!parked) return;
        constraints = StopScenario.parse(text);
        if (constraints == null) {
            message = "예시 음성의 도착 시각과 체류 조건을 읽지 못했어요. 다시 시도하세요.";
            render();
            return;
        }
        Log.i(TAG, "EXAMPLE_STT_FINAL " + text);
        transcript = "인식된 예시 음성  “" + text + "”";
        message = "판교 출발 지점을 확인하고 있어요.";
        locationPending = true;
        render();
        nav.getCurrentLocationInfo();
        ui.postDelayed(() -> {
            if (locationPending) nav.getCurrentLocationInfo();
        }, 1200);
        ui.postDelayed(() -> {
            if (!locationPending) return;
            locationPending = false;
            message = "순정 내비에서 현재 위치를 읽지 못했어요. 다시 시도하세요.";
            render();
        }, 6200);
    }

    private void checkOrigin(CurrentLocationInfo location) {
        if (!locationPending || location == null) return;
        if (Math.abs(location.getLatitude()) < 1 || Math.abs(location.getLongitude()) < 1) return;
        locationPending = false;
        updatePark();
        if (!parked) return;
        if (Math.abs(location.getLatitude() - ORIGIN_LAT) > 0.015
                || Math.abs(location.getLongitude() - ORIGIN_LON) > 0.015) {
            message = "판교 시연 시작점에서 사용할 수 있어요. 순정 내비의 현재 위치를 확인하세요.";
            render();
            return;
        }
        Log.i(TAG, "DEMO_ORIGIN " + location.getLatitude() + "," + location.getLongitude());
        readRoute(Action.START, List.of(), null);
    }

    private void changeRoute() {
        if (!canOperate() || prediction == null || added) return;
        readRoute(detour ? Action.RESTORE : Action.DETOUR, detour ? List.of() : List.of(DETOUR), null);
    }

    private void verifySchedule() {
        if (!canOperate() || prediction == null || added) return;
        readRoute(Action.SCHEDULE, baseStops(), null);
    }

    private void checkForAdd() {
        if (!canOperate() || prediction == null || prediction.chosen() == null || added) return;
        readRoute(Action.CHECK_ADD, baseStops(), prediction.chosen().venue());
    }

    private List<StopPoint> baseStops() { return detour ? List.of(DETOUR) : List.of(); }

    private List<StopPoint> appendVenue(StopScenario.Venue venue) {
        var route = new ArrayList<>(baseStops());
        route.add(new StopPoint(venue.nativeName(), venue.id(), venue.latitude(), venue.longitude(), venue.address()));
        return route;
    }

    private void readRoute(Action action, List<StopPoint> stops, StopScenario.Venue selected) {
        if (!canOperate()) return;
        var request = new RouteRead(action, stops, selected);
        pending = request;
        message = switch (action) {
            case ADD -> "순정 내비에서 경유지와 최종 목적지를 확인하고 있어요.";
            case CHECK_ADD, SCHEDULE -> "순정 내비의 현재 경로를 확인하고 있어요.";
            default -> "분당구청 경로를 확인하고 있어요. 순정 내비가 열리면 하단 앱 아이콘으로 돌아오세요.";
        };
        render();
        try {
            if (request.changesRoute) {
                var destination = new RouteInfo(DEST_LAT, DEST_LON, DEST_LAT, DEST_LON,
                        DESTINATION, "", "", "0", RouteOption.RECOMMENDED, "", false);
                Log.i(TAG, "REQUEST " + action + " dest=" + DESTINATION + " stops=" + stops);
                if (stops.isEmpty()) nav.requestRoute(destination);
                else {
                    var waypoints = new ArrayList<RequestWaypointInfo>();
                    for (int i = 0; i < stops.size(); i++) {
                        waypoints.add(stops.get(i).request(i == 0 ? WaypointIndex.FIRST : WaypointIndex.SECOND));
                    }
                    nav.requestRouteWithWaypoints(new RouteWithWaypointsRequest(destination, waypoints));
                }
            }
            for (int delay : new int[]{400, 1250, 3000, 5500, 8200, 11600, 15700, 20300}) {
                ui.postDelayed(() -> query(request), delay);
            }
            ui.postDelayed(() -> routeFailed(request), 23000);
        } catch (RuntimeException error) {
            Log.w(TAG, "NAV_REQUEST", error);
            routeFailed(request);
        }
    }

    private void query(RouteRead request) {
        if (pending != request || !request.started) return;
        updatePark();
        if (pending != request) return;
        nav.getDestinationInfo();
        nav.getWaypointInfo();
    }

    private void routeFailed(RouteRead request) {
        if (pending != request) return;
        pending = null;
        Log.w(TAG, "ROUTE_UNCONFIRMED " + request.action + " started=" + request.started
                + " destination=" + (request.destination != null) + " waypoints=" + (request.waypoints != null));
        if (request.changesRoute || request.action == Action.CHECK_ADD) clearPrediction();
        message = "순정 내비에서 경로가 일치하는지 확인하지 못했어요. 다시 시도하세요.";
        render();
    }

    private final NaviHelperEventListener events = new NaviHelperEventListener() {
        @Override public void onCurrentLocationInfo(CurrentLocationInfo location) {
            ui.post(() -> checkOrigin(location));
        }

        @Override public void onRouteStarted(RouteStartInfo info) {
            ui.post(() -> {
                var request = pending;
                if (request == null || !request.changesRoute || info == null || !request.firstName().equals(info.getName())) return;
                request.started = true;
                Log.i(TAG, "ROUTE_STARTED " + request.action + " first=" + info.getName());
                ui.postDelayed(() -> query(request), 700);
            });
        }

        @Override public void onDestinationInfo(DestinationInfo destination) {
            ui.post(() -> {
                var request = pending;
                if (request == null || !request.started || !matchesDestination(destination)) return;
                request.destination = destination;
                acceptIfConfirmed(request);
            });
        }

        @Override public void onWayPointInfo(List<WaypointInfo> waypoints) {
            ui.post(() -> {
                var request = pending;
                if (request == null || !request.started || !request.matches(waypoints)) return;
                request.waypoints = List.copyOf(waypoints);
                acceptIfConfirmed(request);
            });
        }

        @Override public void onError(NaviErrorCode error) {
            Log.w(TAG, "NAV_EVENT " + error);
        }
    };

    private static boolean matchesDestination(DestinationInfo info) {
        return info != null && DESTINATION.equals(info.getName()) && info.getRemainTime() > 0
                && Math.abs(info.getLatitude() - DEST_LAT) < 0.00015
                && Math.abs(info.getLongitude() - DEST_LON) < 0.00015;
    }

    private void acceptIfConfirmed(RouteRead request) {
        if (pending != request || request.destination == null || request.waypoints == null) return;
        updatePark();
        if (pending != request) return;
        pending = null;
        int receivedEta = request.destination.getRemainTime();
        Log.i(TAG, "ROUTE_CONFIRMED " + request.action + " dest=" + request.destination.getName()
                + " etaSeconds=" + receivedEta + " stops="
                + request.waypoints.stream().map(w -> w.getName() + "[" + w.getPoiId() + "]").collect(Collectors.joining(", ")));
        previousEta = eta;

        switch (request.action) {
            case START -> {
                eta = receivedEta;
                reference = StopScenario.referenceTime(eta);
                updatePrediction();
                message = "들를 곳을 확인했어요. 정차 중에 경유지를 추가할 수 있어요.";
            }
            case DETOUR, RESTORE -> {
                detour = request.action == Action.DETOUR;
                cause = Cause.ROUTE;
                eta = receivedEta;
                updatePrediction();
                message = "새 경로의 시간을 반영했어요.";
            }
            case SCHEDULE -> {
                if (receivedEta != eta) {
                    eta = receivedEta;
                    cause = Cause.ROUTE;
                    updatePrediction();
                    message = "순정 내비의 시간이 달라졌어요. 마감 변경 시연을 다시 누르세요.";
                } else {
                    scheduleChanged = !scheduleChanged;
                    cause = Cause.SCHEDULE;
                    updatePrediction();
                    message = scheduleChanged ? "시연용 입장 마감을 반영했어요." : "공식 입장 마감으로 복원했어요.";
                }
            }
            case CHECK_ADD -> {
                eta = receivedEta;
                if (previousEta == eta) {
                    readRoute(Action.ADD, appendVenue(request.selected), request.selected);
                    return;
                }
                cause = Cause.ROUTE;
                updatePrediction();
                message = "순정 내비의 시간이 바뀌었어요. 새 예상을 확인하세요.";
            }
            case ADD -> {
                var stop = request.waypoints.get(request.waypoints.size() - 1);
                receipt = StopScenario.confirmed(constraints, request.selected, reference, eta,
                        stop.getRemainTime(), receivedEta, scheduleChanged);
                added = true;
                eta = receivedEta;
                message = receipt.eligible() ? "순정 내비에 경유지를 추가했어요. 최종 목적지는 분당구청이에요."
                        : "순정 내비에 추가했어요. 최신 예상으로는 시간 조건을 맞추기 어려워요.";
                Log.i(TAG, "WAYPOINT_ACCEPTED id=" + stop.getPoiId() + " final=" + request.destination.getName()
                        + " viaEta=" + stop.getRemainTime() + " fits=" + receipt.eligible());
            }
        }
        render();
    }

    private void clearPrediction() {
        prediction = null;
        eta = 0;
        previousEta = 0;
        detour = false;
        scheduleChanged = false;
        added = false;
        receipt = null;
    }

    private void updatePrediction() {
        prediction = StopScenario.evaluate(constraints, reference, eta, detour, scheduleChanged);
        String selected = prediction.chosen() == null ? "none" : prediction.chosen().venue().name();
        Log.i(TAG, "RECOMMEND cause=" + cause + " place=" + selected + " reference=" + StopScenario.clock(reference)
                + " etaSeconds=" + eta + " ecoEntry=" + StopScenario.clock(prediction.eco().entry())
                + " admission=" + StopScenario.clock(scheduleChanged ? StopScenario.DEMO_ADMISSION : StopScenario.OFFICIAL_ADMISSION));
    }

    private void render() {
        if (screen == null || destroyed) return;
        boolean operative = navReady && parked && !busy();
        screen.gear.setText(vehicle == null ? "차량 상태 확인 중" : parked ? "P단 · 조작 가능" : "P단에서 조작하세요");
        screen.notice.setText(vehicle != null && !parked ? "조작하려면 P단에 두세요." : message);
        screen.eta.setText(eta <= 0 ? "판교 출발 · 시연 경로"
                : "순정 내비 주행 " + StopScenario.minutes(eta) + "분 · 시연 기준 " + StopScenario.clock(reference));
        var shown = added ? receipt : prediction == null ? null : prediction.chosen();
        screen.metrics.setVisibility(shown == null ? View.GONE : View.VISIBLE);
        screen.demo.setVisibility(prediction == null ? View.GONE : View.VISIBLE);
        screen.transcript.setText(transcript);
        screen.primary.setText(prediction == null ? "예시 음성으로 시작" : added ? "순정 내비에 추가됨" : "경유지 추가");
        screen.primary.setEnabled(operative && (prediction == null || shown != null && !added));
        screen.reset.setEnabled(operative);
        screen.route.setText(detour ? "경로 복원" : "경로 변경 시연");
        screen.route.setEnabled(operative && !added);
        screen.schedule.setText(scheduleChanged ? "마감 복원" : "마감 변경 시연");
        screen.schedule.setEnabled(operative && !added);

        screen.recommendationLabel.setText(added ? "순정 내비 경유지" : "경유지 추천");
        screen.place.setText(shown != null ? shown.venue().name()
                : prediction == null ? "가는 길에 30분 들를 곳" : "조건에 맞는 경유지가 없어요");
        screen.placeDetail.setText(shown != null ? "실내 30분 · " + shown.venue().parking()
                : "주차할 수 있는 실내 장소를 찾아요.");
        if (shown != null) {
            screen.entry.setText(StopScenario.clock(shown.entry()));
            screen.arrival.setText(StopScenario.clock(shown.finish()));
        }
        renderReason(shown);
    }

    private void renderReason(StopScenario.Estimate shown) {
        if (prediction == null) {
            screen.reasonTitle.setText("추천 기준");
            screen.reason.setText("입장·주차 마감과 실내 30분을 함께 계산해요.");
            screen.change.setText("주차 후 이동과 출차 준비에 각각 3분을 더해요.");
        } else if (added) {
            screen.reasonTitle.setText("순정 내비 확인");
            screen.reason.setText(receipt.eligible() ? "경유지와 최종 목적지를 확인했어요." : "시간 조건을 다시 확인하세요.");
            screen.change.setText(receipt.eligible() ? "실내 체류 30분과 출입 여유 6분을 포함했어요." : receipt.rejection());
        } else {
            var eco = prediction.eco();
            screen.reasonTitle.setText(switch (cause) {
                case BASE -> "추천 이유";
                case ROUTE -> "경로가 바뀌었어요";
                case SCHEDULE -> "입장 마감이 바뀌었어요";
            });
            int cutoff = scheduleChanged ? StopScenario.DEMO_ADMISSION : StopScenario.OFFICIAL_ADMISSION;
            screen.reason.setText(eco.eligible() ? "학습원은 마감 전에 입장할 수 있어요."
                    : eco.entry() >= cutoff ? "학습원 입장이 마감돼요." : "학습원은 이번 경로에서 제외했어요.");
            String detail = !eco.eligible() ? eco.rejection()
                    : "학습원 입장 예상 " + StopScenario.clock(eco.entry()) + " · 입장 마감 " + StopScenario.clock(cutoff);
            if (cause == Cause.ROUTE && previousEta > 0 && previousEta != eta) {
                detail += "\n순정 내비 " + StopScenario.minutes(previousEta) + "분 → " + StopScenario.minutes(eta) + "분";
            }
            if (cause == Cause.SCHEDULE) {
                detail += "\n순정 내비 " + StopScenario.minutes(eta) + "분 유지";
                if (scheduleChanged) detail += " · 시연용 마감";
            }
            if (shown == null) detail += "\n최종 목적지까지의 경로를 유지해요.";
            screen.change.setText(detail);
        }
    }
}
