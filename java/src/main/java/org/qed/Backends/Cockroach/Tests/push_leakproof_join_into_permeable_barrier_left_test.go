package rulescript_test

import (
	"testing"
)

func TestPushLeakproofJoinIntoPermeableBarrierLeft(t *testing.T) {
	testPushJoinThroughBarrier(t, "PushLeakproofJoinIntoPermeableBarrierLeft", true)
}

func TestSQLPushLeakproofJoinIntoPermeableBarrierLeft(t *testing.T) {
	expectSQLFiresWith(t, "PushLeakproofJoinIntoPermeableBarrierLeft",
		"SELECT * FROM (SELECT x, y FROM u) q JOIN (SELECT a, b FROM nn) r ON q.x = r.a",
		sqlHelpers{names: []string{"PruneScanCols"}})
}
