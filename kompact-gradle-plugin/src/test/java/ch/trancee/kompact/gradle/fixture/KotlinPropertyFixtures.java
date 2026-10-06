package ch.trancee.kompact.gradle.fixture;

public final class KotlinPropertyFixtures {
    private KotlinPropertyFixtures() {}

    public static Object hiddenValueReceiver() {
        return new HiddenValueReceiver();
    }

    static final class HiddenValueReceiver {
        public Object getValue() {
            return "hidden";
        }
    }
}
