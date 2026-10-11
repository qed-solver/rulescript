package rulescript_test

import (
	"testing"
)

func TestPruneWithScanCols(t *testing.T) {
	testPruneScanCols(t, "PruneWithScanCols")
}

func TestSQLPruneWithScanCols(t *testing.T) {
	expectSQLFiresOrNot(t, "PruneWithScanCols", "SELECT a+b FROM (VALUES (1,2,3),(4,5,6)) AS v(a,b,c)", true)
}
