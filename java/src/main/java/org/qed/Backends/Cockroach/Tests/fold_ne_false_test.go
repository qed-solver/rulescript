package rulescript_test

import (
	"testing"
)

func TestFoldNeFalse(t *testing.T) { testBooleanComparisonFold(t, "FoldNeFalse", true, false) }

func TestSQLFoldNeFalse(t *testing.T) {
	expectSQLRewrites(t, "FoldNeFalse", "SELECT x FROM u WHERE (x > 0) <> FALSE")
}
