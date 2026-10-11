package rulescript_test

import (
	"testing"
)

func TestCommuteConstInequality(t *testing.T) {
	testCommuteInequality(t, "CommuteConstInequality", false)
}

func TestSQLCommuteConstInequality(t *testing.T) {
	expectSQLRewrites(t, "CommuteConstInequality", "SELECT a, b FROM (VALUES (1)) AS l(a), (VALUES (2)) AS r(b) WHERE a < b")
}
