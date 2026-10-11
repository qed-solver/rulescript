package rulescript_test

import (
	"testing"
)

func TestInlineSelectVirtualColumns(t *testing.T) {
	testComputedProjectFilterPushdown(t, "InlineSelectVirtualColumns", true)
}

func TestSQLInlineSelectVirtualColumns(t *testing.T) {
	expectSQLRewrites(t, "InlineSelectVirtualColumns", "SELECT * FROM (SELECT x+y AS f, x*y AS g, x, y FROM (VALUES (1,2),(3,4)) AS v(x,y)) AS q WHERE f>5 AND g<10")
}
