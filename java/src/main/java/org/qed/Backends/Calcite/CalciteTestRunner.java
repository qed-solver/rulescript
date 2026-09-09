package org.qed.Backends.Calcite;

public final class CalciteTestRunner {
    private CalciteTestRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one test class name");
        }
        Class.forName(args[0]).getMethod("runTest").invoke(null);
    }
}
