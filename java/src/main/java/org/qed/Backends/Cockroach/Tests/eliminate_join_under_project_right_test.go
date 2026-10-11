package rulescript_test

import (
	"testing"
)

func TestEliminateJoinUnderProjectRight(t *testing.T) {
	testEliminateSelfJoinProject(t, "EliminateJoinUnderProjectRight", true)
}

func TestSQLEliminateJoinUnderProjectRight(t *testing.T) {
	expectSQLRepeatedScan(t, "EliminateJoinUnderProjectRight", "SELECT r.a FROM nn AS l JOIN nn AS r ON l.a=r.a", "inner-join", "scan nn", true)
}
