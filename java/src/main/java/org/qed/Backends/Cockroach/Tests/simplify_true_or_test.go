package rulescript_test

import (
	"testing"
)

func TestSimplifyTrueOr(t *testing.T) { testSimplifyOrLiteral(t, "SimplifyTrueOr", true, true) }

func TestSQLSimplifyTrueOr(t *testing.T) {
	expectSQLRewrites(t, "SimplifyTrueOr", "SELECT x FROM u WHERE TRUE OR x > 0")
}
