package rulescript_test

import (
	"testing"
)

func TestSimplifyLeftJoin(t *testing.T) {
	testSimplifySelfJoin(t, "SimplifyLeftJoin", "left")
}

func TestSQLSimplifyLeftJoin(t *testing.T) {
	expectSQLRepeatedScan(t, "SimplifyLeftJoin", "SELECT l.a,r.a FROM nn AS l LEFT JOIN nn AS r ON l.a=r.a", "left-join", "inner-join", true)
}
