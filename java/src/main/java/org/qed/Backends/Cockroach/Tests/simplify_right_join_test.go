package rulescript_test

import (
	"testing"
)

func TestSimplifyRightJoin(t *testing.T) {
	testSimplifySelfJoin(t, "SimplifyRightJoin", "right")
}

func TestSQLSimplifyRightJoin(t *testing.T) {
	expectSQLRepeatedScan(t, "SimplifyRightJoin", "SELECT l.a,r.a FROM nn AS l FULL JOIN nn AS r ON l.a=r.a", "full-join", "left-join", true)
}
