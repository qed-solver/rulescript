package rulescript_test

import (
	"testing"
)

func TestPruneExplainCols(t *testing.T) {
	testPruneOneInputCol(t, "PruneExplainCols")
}

func TestSQLPruneExplainCols(t *testing.T) {
	expectSQLFiresOrNot(t, "PruneExplainCols", "SELECT a+1 FROM (VALUES (1,2),(3,4)) AS v(a,b)", true)
}
