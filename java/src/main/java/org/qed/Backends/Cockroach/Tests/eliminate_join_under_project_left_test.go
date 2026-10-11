package rulescript_test

import (
	"testing"
)

func TestEliminateJoinUnderProjectLeft(t *testing.T) {
	testEliminateSelfJoinProject(t, "EliminateJoinUnderProjectLeft", false)
}

func TestSQLEliminateJoinUnderProjectLeft(t *testing.T) {
	expectSQLRepeatedScan(t, "EliminateJoinUnderProjectLeft", "SELECT l.a FROM nn AS l LEFT JOIN nn AS r ON l.a=r.a", "left-join", "scan nn", true)
}
