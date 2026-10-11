package rulescript_test

import (
	"testing"
)

func TestFoldEqTrue(t *testing.T) { testBooleanComparisonFold(t, "FoldEqTrue", false, true) }

func TestSQLFoldEqTrue(t *testing.T) {
	expectSQLRewrites(t, "FoldEqTrue", "SELECT x FROM u WHERE (x > 0) = TRUE")
}
