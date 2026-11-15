package app.pleos.stop30;

public final class StopScenarioTest {
    public static void main(String[] args) {
        var constraints = StopScenario.parse("6시까지 도착해야 하는데 가는 길에 주차되는 실내 장소에서 30분 보내고 싶어");
        check(constraints != null && constraints.deadline() == 64800 && constraints.visitTime() == 1800,
                "The bundled phrase supplies the deadline and dwell time");
        check(StopScenario.parse("6시까지 주차되는 실내에 가고 싶어") == null, "The visit length is required");

        int reference = StopScenario.referenceTime(900);
        var baseline = StopScenario.evaluate(constraints, reference, 900, false, false);
        check(baseline.chosen().venue() == StopScenario.ECO && baseline.eco().entry() == 61080,
                "Select the ecology center when the expected entry is 16:58");
        var rerouted = StopScenario.evaluate(constraints, reference, 1080, true, false);
        check(rerouted.chosen().venue() == StopScenario.BOOK && rerouted.eco().entry() == 61260,
                "A three-minute route delay puts the ecology center past its cutoff");
        var earlierAdmission = StopScenario.evaluate(constraints, reference, 900, false, true);
        check(earlierAdmission.chosen().venue() == StopScenario.BOOK && earlierAdmission.eco().entry() == 61080,
                "An earlier closing time changes the recommendation at the same route ETA");
        var restored = StopScenario.evaluate(constraints, reference, 900, false, false);
        check(restored.equals(baseline), "Restoring published hours restores the original result");

        var atCutoff = StopScenario.confirmed(constraints, StopScenario.ECO, reference, 900, 796, 1080, false);
        check(atCutoff.entry() == 61200 && !atCutoff.eligible(), "Arrival at the admission cutoff misses it");
        var tooLate = StopScenario.evaluate(constraints, 64000, 900, false, false);
        check(tooLate.chosen() == null, "No recommendation if the destination deadline cannot be met");
        System.out.println("StopScenarioTest passed");
    }

    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
