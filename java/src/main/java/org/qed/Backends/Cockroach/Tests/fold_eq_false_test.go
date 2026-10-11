package rulescript_test

import (
	"testing"
)

func TestFoldEqFalse(t *testing.T) { testBooleanComparisonFold(t, "FoldEqFalse", false, false) }

func TestSQLFoldEqFalse(t *testing.T) {
	expectSQLRewrites(t, "FoldEqFalse", "SELECT x FROM u WHERE (x > 0) = FALSE")
}
