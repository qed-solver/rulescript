package rulescript_test

import (
	"testing"
)

func TestFoldNeTrue(t *testing.T) { testBooleanComparisonFold(t, "FoldNeTrue", true, true) }

func TestSQLFoldNeTrue(t *testing.T) {
	expectSQLRewrites(t, "FoldNeTrue", "SELECT x FROM u WHERE (x > 0) <> TRUE")
}
