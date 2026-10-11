package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestEliminateJoinUnderGroupByLeft(t *testing.T) {
	rule := loadRule(t, "EliminateJoinUnderGroupByLeft")
	e := newEnv(t)
	left, lc := e.scan("u", 1)
	right, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	joined := e.f.ConstructLeftJoin(left, right, on, &memo.JoinPrivate{})
	before := e.f.ConstructGroupBy(joined, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0]),
	})
	want := e.f.ConstructGroupBy(left, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0]),
	})
	e.expectRewrite(rule, before, want)

	inner := e.f.ConstructInnerJoin(left, right, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(inner, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(lc[0]),
	}))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(joined, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(rc[0]),
	}))
}

func TestSQLEliminateJoinUnderGroupByLeft(t *testing.T) {
	expectSQLFiresOrNot(t, "EliminateJoinUnderGroupByLeft", "SELECT l.a FROM (VALUES (1),(2)) AS l(a) LEFT JOIN (VALUES (1),(3)) AS r(b) ON l.a=r.b GROUP BY l.a", false)
}
