package rulescript_test

import (
	"testing"
)

func TestCommuteVarInequality(t *testing.T) {
	testCommuteInequality(t, "CommuteVarInequality", true)
}

func TestSQLCommuteVarInequality(t *testing.T) {
	expectSQLRewrites(t, "CommuteVarInequality", "SELECT a, b FROM (VALUES (1)) AS l(a), (VALUES (2)) AS r(b) WHERE a <= b")
}
