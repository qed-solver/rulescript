package rulescript_test

import (
	"testing"
)

func TestPushLeakproofJoinIntoPermeableBarrierRight(t *testing.T) {
	testPushJoinThroughBarrier(t, "PushLeakproofJoinIntoPermeableBarrierRight", false)
}

func TestSQLPushLeakproofJoinIntoPermeableBarrierRight(t *testing.T) {
	expectSQLFiresWith(t, "PushLeakproofJoinIntoPermeableBarrierRight",
		"SELECT * FROM (SELECT a, b FROM nn) r JOIN (SELECT x, y FROM u) q ON q.x = r.a",
		sqlHelpers{names: []string{"PruneScanCols"}})
}
