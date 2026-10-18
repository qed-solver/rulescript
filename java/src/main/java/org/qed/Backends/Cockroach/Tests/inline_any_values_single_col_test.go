package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestInlineAnyValuesSingleCol(t *testing.T) {
	rule := loadRule(t, "InlineAnyValuesSingleCol")
	e := newEnv(t)
	s, sc := e.scan("t", 1)
	c1 := e.f.Metadata().AddColumn("c1", types.Int)
	c3 := e.f.Metadata().AddColumn("c3", types.Int)
	out := e.f.Metadata().AddColumn("u", types.Int)
	one := e.intValues([]opt.ColumnID{c1}, []int{1})
	three := e.intValues([]opt.ColumnID{c3}, []int{3})
	union := e.f.ConstructUnionAll(one, three, &memo.SetPrivate{
		LeftCols: opt.ColList{c1}, RightCols: opt.ColList{c3}, OutCols: opt.ColList{out},
	})
	on := e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(out)))
	before := e.f.ConstructSemiJoin(s, union, on, &memo.JoinPrivate{})
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(
		e.f.ConstructEq(e.v(sc[0]), e.num(1)),
		e.f.ConstructEq(e.v(sc[0]), e.num(3)),
	)))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(s, union, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSemiJoinApply(s, union, on, &memo.JoinPrivate{}))
	lt := e.filters(e.f.ConstructLt(e.v(sc[0]), e.v(out)))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(s, union, lt, &memo.JoinPrivate{}))
	c2 := e.f.Metadata().AddColumn("c2", types.Int)
	out2 := e.f.Metadata().AddColumn("u2", types.Int)
	two := e.intValues([]opt.ColumnID{c2}, []int{2})
	union2 := e.f.ConstructUnionAll(one, two, &memo.SetPrivate{
		LeftCols: opt.ColList{c1}, RightCols: opt.ColList{c2}, OutCols: opt.ColList{out2},
	})
	on2 := e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(out2)))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(s, union2, on2, &memo.JoinPrivate{}))
}
