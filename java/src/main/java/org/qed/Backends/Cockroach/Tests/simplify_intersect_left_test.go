package rulescript_test

import (
	"testing"
)

func TestSimplifyIntersectLeft(t *testing.T) {
	testSimplifyIntersect(t, "SimplifyIntersectLeft", false)
}

func TestSQLSimplifyIntersectLeft(t *testing.T) {
	expectSQLRewrites(t, "SimplifyIntersectLeft", "SELECT x FROM u INTERSECT SELECT a FROM t")
}
