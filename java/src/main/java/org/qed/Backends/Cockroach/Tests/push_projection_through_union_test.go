package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushProjectionThroughUnion(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushProjectionThroughUnion")
	left, leftCols := e.scan("u", 2)
	right, rightCols := e.scan("t", 2)
	unionCols := opt.ColList{
		e.f.Metadata().AddColumn("union_a", types.Int),
		e.f.Metadata().AddColumn("union_b", types.Int),
	}
	union := e.f.ConstructUnionAll(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{leftCols[0], leftCols[1]},
		RightCols: opt.ColList{rightCols[0], rightCols[1]},
		OutCols: unionCols,
	})
	projectedCol := e.f.Metadata().AddColumn("sum", types.Int)
	before := e.f.ConstructProject(
		union,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(unionCols[0]), e.v(unionCols[1])), projectedCol)},
		opt.ColSet{},
	)

	got, ok := rule.Apply(&e.f, before)
	if !ok {
		t.Fatal("PushProjectionThroughUnion did not apply")
	}
	unionOut := got.(*memo.ProjectExpr).Input.(*memo.UnionAllExpr).SetPrivate.OutCols[0]
	sum := func(a, b opt.ColumnID) opt.ScalarExpr { return e.f.ConstructPlus(e.v(a), e.v(b)) }
	leftProjected := e.f.ConstructProject(left,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(sum(leftCols[0], leftCols[1]), projectedCol)}, opt.ColSet{})
	rightSum := e.f.Metadata().AddColumn("right_sum", types.Int)
	rightProjected := e.f.ConstructProject(right,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(sum(rightCols[0], rightCols[1]), projectedCol)}, opt.ColSet{})
	rightRenamed := e.f.ConstructProject(rightProjected,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(projectedCol), rightSum)}, opt.ColSet{})
	wantUnion := e.f.ConstructUnionAll(leftProjected, rightRenamed, &memo.SetPrivate{
		LeftCols: opt.ColList{projectedCol},
		RightCols: opt.ColList{rightSum},
		OutCols: opt.ColList{unionOut},
	})
	want := e.f.ConstructProject(wantUnion,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(unionOut), projectedCol)}, opt.ColSet{})
	if g, w := canonicalColumns(e.format(got)), canonicalColumns(e.format(want)); g != w {
		t.Fatalf("PushProjectionThroughUnion produced an unexpected plan\ngot:\n%s\nwant:\n%s", g, w)
	}

	other, otherCols := e.scan("u", 2)
	nonUnion := e.f.ConstructProject(
		other,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(otherCols[0]), e.v(otherCols[1])), e.f.Metadata().AddColumn("other_sum", types.Int))},
		opt.ColSet{},
	)
	e.expectNoMatch(rule, nonUnion)
}
