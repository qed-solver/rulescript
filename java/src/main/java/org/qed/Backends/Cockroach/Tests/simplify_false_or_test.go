package rulescript_test

import (
	"testing"
)

func TestSimplifyFalseOr(t *testing.T) { testSimplifyOrLiteral(t, "SimplifyFalseOr", false, true) }

func TestSQLSimplifyFalseOr(t *testing.T) {
	expectSQLRewrites(t, "SimplifyFalseOr", "SELECT x FROM u WHERE FALSE OR x > 0")
}
