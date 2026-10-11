package rulescript_test

import (
	"testing"
)

func TestInlineSelectConstants(t *testing.T) {
	testInlineSelectEqualColumns(t, "InlineSelectConstants")
}

func TestSQLInlineSelectConstants(t *testing.T) {
	expectSQLRewrites(t, "InlineSelectConstants", "SELECT l.a FROM (VALUES (1), (2)) AS l(a) JOIN (VALUES (1)) AS r(b) ON l.a = r.b WHERE l.a > 0")
}
