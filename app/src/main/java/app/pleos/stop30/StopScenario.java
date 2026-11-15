package app.pleos.stop30;

import java.util.Locale;

/** Time budgets for the recorded Pangyo demonstration. All values are seconds. */
final class StopScenario {
    private static final int ACCESS = 3 * 60;
    private static final int DIRECT_ROAD = 428;
    private static final int DETOUR_ROAD = 803;
    static final int OFFICIAL_ADMISSION = 17 * 3600;
    static final int DEMO_ADMISSION = 16 * 3600 + 45 * 60;

    record Constraints(int deadline, int visitTime) {}

    record Venue(String name, String nativeName, String id, double latitude, double longitude,
                 String address, String parking, int opens, int lastAdmission, int parkingCutoff,
                 int closes, int parkingCloses, int directTo, int detourTo, int onward) {}

    record Estimate(Venue venue, int parkingAt, int entry, int finish, int extra, String rejection) {
        boolean eligible() { return rejection == null; }
    }

    record Result(Estimate eco, Estimate chosen) {}

    static final Venue ECO = new Venue(
            "판교환경생태학습원", "판교환경생태학습원", "36khde543dg5cm5nqhe3",
            37.39974722, 127.10415833, "성남시 분당구 대왕판교로645번길 21", "학습원 주차장",
            9 * 3600, OFFICIAL_ADMISSION, 17 * 3600, 18 * 3600, 18 * 3600, 204, 579, 274);

    static final Venue BOOK = new Venue(
            "교보문고 판교점", "교보문고 판교바로드림센터점", "36khde3dk2mfcm5nqhe5",
            37.39200277, 127.11161388, "성남시 분당구 판교역로146번길 20", "현대백화점 판교점 주차장",
            10 * 3600 + 30 * 60, 20 * 3600 + 30 * 60, 20 * 3600 + 30 * 60,
            20 * 3600 + 30 * 60, 20 * 3600 + 30 * 60, 317, 692, 224);

    static Constraints parse(String sentence) {
        if (sentence == null) return null;
        String phrase = sentence.replaceAll("\\s", "");
        boolean deadline = phrase.contains("6시") || phrase.contains("여섯시") || phrase.contains("육시");
        boolean visit = phrase.contains("30분") || phrase.contains("삼십분");
        if (!deadline || !visit || !phrase.contains("까지") || !phrase.contains("주차") || !phrase.contains("실내")) {
            return null;
        }
        return new Constraints(18 * 3600, 30 * 60);
    }

    static int referenceTime(int nativeEta) {
        // Set the initial estimated entry two minutes before the official cutoff, then freeze this clock.
        return OFFICIAL_ADMISSION - 120 - approach(ECO, nativeEta, false) - ACCESS;
    }

    static Result evaluate(Constraints constraints, int reference, int nativeEta,
                           boolean detour, boolean earlyAdmission) {
        var eco = estimate(constraints, ECO, reference, nativeEta, detour,
                earlyAdmission ? DEMO_ADMISSION : OFFICIAL_ADMISSION);
        var book = estimate(constraints, BOOK, reference, nativeEta, detour, BOOK.lastAdmission());
        if (eco.eligible() && (!book.eligible() || eco.extra() <= book.extra())) return new Result(eco, eco);
        return new Result(eco, book.eligible() ? book : null);
    }

    private static Estimate estimate(Constraints constraints, Venue venue, int reference,
                                     int nativeEta, boolean detour, int admission) {
        int parkingAt = reference + approach(venue, nativeEta, detour);
        int entry = parkingAt + ACCESS;
        int extra = Math.max(0, roadTo(venue, detour) + venue.onward() - baselineRoad(detour));
        int finish = reference + nativeEta + extra + constraints.visitTime() + 2 * ACCESS;
        String rejection = rejection(constraints, venue, admission, parkingAt, entry, finish);
        return new Estimate(venue, parkingAt, entry, finish, extra, rejection);
    }

    static Estimate confirmed(Constraints constraints, Venue venue, int reference, int baseEta,
                              int parkingEta, int finishEta, boolean earlyAdmission) {
        int parkingAt = reference + parkingEta;
        int entry = parkingAt + ACCESS;
        int finish = reference + finishEta + constraints.visitTime() + 2 * ACCESS;
        int admission = earlyAdmission && venue == ECO ? DEMO_ADMISSION : venue.lastAdmission();
        return new Estimate(venue, parkingAt, entry, finish, Math.max(0, finishEta - baseEta),
                rejection(constraints, venue, admission, parkingAt, entry, finish));
    }

    private static String rejection(Constraints constraints, Venue venue, int admission,
                                    int parkingAt, int entry, int finish) {
        if (entry < venue.opens()) return "예상 도착 시각에 아직 운영 전입니다.";
        if (entry >= admission) return "입장 예상 " + clock(entry) + " · 입장 마감 " + clock(admission);
        if (parkingAt >= venue.parkingCutoff()) return "주차장 입차 마감 " + clock(venue.parkingCutoff());
        if (entry + constraints.visitTime() > venue.closes()) return "30분 머물기 전에 시설이 닫습니다.";
        if (entry + constraints.visitTime() + ACCESS > venue.parkingCloses()) return "출차 전에 주차장이 닫습니다.";
        if (finish > constraints.deadline()) return "분당구청에 " + clock(constraints.deadline()) + "까지 도착하기 어렵습니다.";
        return null;
    }

    private static int baselineRoad(boolean detour) { return detour ? DETOUR_ROAD : DIRECT_ROAD; }

    private static int roadTo(Venue venue, boolean detour) { return detour ? venue.detourTo() : venue.directTo(); }

    private static int approach(Venue venue, int nativeEta, boolean detour) {
        return roadTo(venue, detour) + Math.max(0, nativeEta - baselineRoad(detour));
    }

    static String clock(int time) {
        return String.format(Locale.KOREA, "%02d:%02d", time / 3600, time / 60 % 60);
    }

    static int minutes(int seconds) { return (seconds + 59) / 60; }

    private StopScenario() {}
}
