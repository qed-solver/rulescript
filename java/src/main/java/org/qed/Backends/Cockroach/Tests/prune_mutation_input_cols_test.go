package rulescript_test

import (
	"testing"
)

func TestPruneMutationInputCols(t *testing.T) {
	testPruneOneInputCol(t, "PruneMutationInputCols")
}

func TestSQLPruneMutationInputCols(t *testing.T) {
	expectSQLFiresOrNot(t, "PruneMutationInputCols", "SELECT a+1 FROM (VALUES (1,2),(3,4)) AS v(a,b)", true)
}
