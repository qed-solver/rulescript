package rulescript_test

import (
	"testing"
)

func TestPushLeakproofJoinIntoPermeableBarrierLeft(t *testing.T) {
	testPushJoinThroughBarrier(t, "PushLeakproofJoinIntoPermeableBarrierLeft", true)
}
