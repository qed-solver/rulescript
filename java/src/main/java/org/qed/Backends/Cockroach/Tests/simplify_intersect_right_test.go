package rulescript_test

import (
	"testing"
)

func TestSimplifyIntersectRight(t *testing.T) {
	testSimplifyIntersect(t, "SimplifyIntersectRight", true)
}

func TestSQLSimplifyIntersectRight(t *testing.T) {
	expectSQLRewrites(t, "SimplifyIntersectRight", "SELECT x FROM u INTERSECT SELECT a FROM t")
}
