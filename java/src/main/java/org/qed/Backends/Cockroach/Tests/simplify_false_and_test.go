package rulescript_test

import (
	"testing"
)

func TestSimplifyFalseAnd(t *testing.T) { testSimplifyAndFalse(t, "SimplifyFalseAnd", true) }

func TestSQLSimplifyFalseAnd(t *testing.T) {
	expectSQLRewrites(t, "SimplifyFalseAnd", "SELECT x FROM u WHERE FALSE AND x > 0")
}
