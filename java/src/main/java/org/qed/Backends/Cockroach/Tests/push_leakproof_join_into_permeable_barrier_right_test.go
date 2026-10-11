package rulescript_test

import (
	"testing"
)

func TestPushLeakproofJoinIntoPermeableBarrierRight(t *testing.T) {
	testPushJoinThroughBarrier(t, "PushLeakproofJoinIntoPermeableBarrierRight", false)
}
