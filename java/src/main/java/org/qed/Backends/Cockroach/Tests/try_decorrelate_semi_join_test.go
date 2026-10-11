package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestTryDecorrelateSemiJoin(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateSemiJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructSemiJoinApply(l, r, on, &memo.JoinPrivate{})
	inner := e.f.ConstructInnerJoinApply(l, r, on, &memo.JoinPrivate{})
	want := e.f.ConstructGroupBy(inner, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(lc[0])})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, r, on, &memo.JoinPrivate{}))
	nonuniqueCol := e.f.Metadata().AddColumn("nonunique", types.Int)
	nonunique := e.intValues([]opt.ColumnID{nonuniqueCol}, []int{1}, []int{1})
	e.expectNoMatch(rule, e.f.ConstructSemiJoinApply(nonunique, r,
		e.filters(e.f.ConstructEq(e.v(nonuniqueCol), e.v(rc[0]))), &memo.JoinPrivate{}))
}
