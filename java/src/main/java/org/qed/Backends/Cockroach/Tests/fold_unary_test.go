package rulescript_test

import (
	"testing"
)

func TestFoldUnary(t *testing.T) { testNotLiteralFold(t, "FoldUnary", true) }

func TestSQLFoldUnary(t *testing.T) {
	expectSQLRewrites(t, "FoldUnary", "SELECT x FROM u WHERE NOT TRUE")
}
