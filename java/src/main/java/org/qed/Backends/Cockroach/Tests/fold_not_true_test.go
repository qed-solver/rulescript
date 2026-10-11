package rulescript_test

import (
	"testing"
)

func TestFoldNotTrue(t *testing.T) { testNotLiteralFold(t, "FoldNotTrue", true) }

func TestSQLFoldNotTrue(t *testing.T) {
	expectSQLRewrites(t, "FoldNotTrue", "SELECT x FROM u WHERE NOT TRUE")
}
