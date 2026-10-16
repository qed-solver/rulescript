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
	s, cols := e.scan("u", 1)
	leftCol := e.f.Metadata().AddColumn("v1", types.Int)
	rightCol := e.f.Metadata().AddColumn("v2", types.Int)
	out := e.f.Metadata().AddColumn("out", types.Int)
	union := func(second int) memo.RelExpr {
		return e.f.ConstructUnionAll(
			e.intValues([]opt.ColumnID{leftCol}, []int{1}),
			e.intValues([]opt.ColumnID{rightCol}, []int{second}),
			&memo.SetPrivate{
				LeftCols: opt.ColList{leftCol}, RightCols: opt.ColList{rightCol}, OutCols: opt.ColList{out},
			})
	}
	on := e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(out)))
	before := e.f.ConstructSemiJoin(s, union(3), on, &memo.JoinPrivate{})
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(
		e.f.ConstructEq(e.v(cols[0]), e.num(1)), e.f.ConstructEq(e.v(cols[0]), e.num(3)))))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(s, union(4), on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(s, union(3), on, &memo.JoinPrivate{}))
}
