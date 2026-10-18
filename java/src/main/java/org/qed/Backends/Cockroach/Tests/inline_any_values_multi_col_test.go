package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestInlineAnyValuesMultiCol(t *testing.T) {
	rule := loadRule(t, "InlineAnyValuesMultiCol")
	e := newEnv(t)
	s, sc := e.scan("t", 2)
	p := opt.ColList{e.f.Metadata().AddColumn("p0", types.Int), e.f.Metadata().AddColumn("p1", types.Int)}
	q := opt.ColList{e.f.Metadata().AddColumn("q0", types.Int), e.f.Metadata().AddColumn("q1", types.Int)}
	out := opt.ColList{e.f.Metadata().AddColumn("o0", types.Int), e.f.Metadata().AddColumn("o1", types.Int)}
	first := e.intValues(p, []int{1, 2})
	second := e.intValues(q, []int{3, 4})
	values := e.f.ConstructUnionAll(first, second, &memo.SetPrivate{LeftCols: p, RightCols: q, OutCols: out})
	on := e.filters(
		e.f.ConstructEq(e.v(sc[0]), e.v(out[0])),
		e.f.ConstructEq(e.v(sc[1]), e.v(out[1])),
	)
	before := e.f.ConstructSemiJoin(s, values, on, &memo.JoinPrivate{})
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(
		e.f.ConstructAnd(e.f.ConstructEq(e.v(sc[0]), e.num(1)), e.f.ConstructEq(e.v(sc[1]), e.num(2))),
		e.f.ConstructAnd(e.f.ConstructEq(e.v(sc[0]), e.num(3)), e.f.ConstructEq(e.v(sc[1]), e.num(4))),
	)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(s, values, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(s, values, e.filters(
		e.f.ConstructEq(e.v(sc[0]), e.v(out[0])),
	), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(s, values, e.filters(
		e.f.ConstructEq(e.v(sc[0]), e.v(out[1])),
		e.f.ConstructEq(e.v(sc[1]), e.v(out[0])),
	), &memo.JoinPrivate{}))
}
