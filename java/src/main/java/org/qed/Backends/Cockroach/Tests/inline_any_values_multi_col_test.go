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
	s, cols := e.scan("t", 2)
	left := opt.ColList{e.f.Metadata().AddColumn("l0", types.Int), e.f.Metadata().AddColumn("l1", types.Int)}
	right := opt.ColList{e.f.Metadata().AddColumn("r0", types.Int), e.f.Metadata().AddColumn("r1", types.Int)}
	out := opt.ColList{e.f.Metadata().AddColumn("o0", types.Int), e.f.Metadata().AddColumn("o1", types.Int)}
	union := func(last int) memo.RelExpr {
		return e.f.ConstructUnionAll(e.intValues(left, []int{1, 2}), e.intValues(right, []int{3, last}),
			&memo.SetPrivate{LeftCols: left, RightCols: right, OutCols: out})
	}
	on := e.filters(
		e.f.ConstructEq(e.v(cols[0]), e.v(out[0])),
		e.f.ConstructEq(e.v(cols[1]), e.v(out[1])))
	before := e.f.ConstructSemiJoin(s, union(4), on, &memo.JoinPrivate{})
	tuple := func(x, y int) opt.ScalarExpr {
		return e.f.ConstructAnd(
			e.f.ConstructEq(e.v(cols[0]), e.num(x)), e.f.ConstructEq(e.v(cols[1]), e.num(y)))
	}
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(tuple(1, 2), tuple(3, 4))))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(s, union(5), on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(s, union(4), on, &memo.JoinPrivate{}))
}
