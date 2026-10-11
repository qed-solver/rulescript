package rulescript_test

import (
	"testing"
)

func TestFoldNotFalse(t *testing.T) { testNotLiteralFold(t, "FoldNotFalse", false) }

func TestSQLFoldNotFalse(t *testing.T) {
	expectSQLRewrites(t, "FoldNotFalse", "SELECT x FROM u WHERE NOT FALSE")
}
