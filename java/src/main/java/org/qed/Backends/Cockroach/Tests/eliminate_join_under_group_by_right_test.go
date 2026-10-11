package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestEliminateJoinUnderGroupByRight(t *testing.T) {
	rule := loadRule(t, "EliminateJoinUnderGroupByRight")
	e := newEnv(t)
	l, lc := e.scan("nn", 1)
	r, rc := e.scan("nn", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	join := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	before := e.f.ConstructGroupBy(join, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])})
	want := e.f.ConstructGroupBy(r, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(join, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])}))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}), nil,
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])}))
}

func TestSQLEliminateJoinUnderGroupByRight(t *testing.T) {
	expectSQLRepeatedScan(t, "EliminateJoinUnderGroupByRight", "SELECT r.a FROM nn AS l JOIN nn AS r ON l.a=r.a GROUP BY r.a", "group-by", "group-by", false)
}
