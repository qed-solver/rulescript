package rulescript_test

import (
	"testing"
)

func TestPushSelectIntoInlinableProject(t *testing.T) {
	testComputedProjectFilterPushdown(t, "PushSelectIntoInlinableProject", false)
}

func TestSQLPushSelectIntoInlinableProject(t *testing.T) {
	expectSQLRewrites(t, "PushSelectIntoInlinableProject", "SELECT * FROM (SELECT x+y AS f, x*y AS g, x, y FROM (VALUES (1,2),(3,4)) AS v(x,y)) AS q WHERE f>5 AND g<10")
}
