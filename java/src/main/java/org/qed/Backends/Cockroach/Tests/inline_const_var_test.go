package rulescript_test

import (
	"testing"
)

func TestInlineConstVar(t *testing.T) {
	testInlineSelectEqualColumns(t, "InlineConstVar")
}

func TestSQLInlineConstVar(t *testing.T) {
	expectSQLRewrites(t, "InlineConstVar", "SELECT l.a FROM (VALUES (1), (2)) AS l(a) JOIN (VALUES (1)) AS r(b) ON l.a = r.b WHERE l.a > 0")
}
