package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestUnionPullUpConstants(t *testing.T) {
	rule := loadRule(t, "UnionPullUpConstants")
	e := newEnv(t)
	leftScan, lc := e.scan("t", 2)
	rightScan, rc := e.scan("t", 2)
	leftConstant := e.f.Metadata().AddColumn("left_constant", types.Bool)
	rightConstant := e.f.Metadata().AddColumn("right_constant", types.Bool)
	left := e.f.ConstructProject(leftScan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTrue(), leftConstant),
	}, opt.MakeColSet(lc[1]))
	right := e.f.ConstructProject(rightScan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTrue(), rightConstant),
	}, opt.MakeColSet(rc[1]))
	unionConstant := e.f.Metadata().AddColumn("union_constant", types.Bool)
	unionValue := e.f.Metadata().AddColumn("union_value", types.Int)
	before := e.f.ConstructUnionAll(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{leftConstant, lc[1]}, RightCols: opt.ColList{rightConstant, rc[1]}, OutCols: opt.ColList{unionConstant, unionValue},
	})

	leftInner := e.f.ConstructProject(leftScan, memo.ProjectionsExpr{
	}, opt.MakeColSet(lc[1]))
	rightInner := e.f.ConstructProject(rightScan, memo.ProjectionsExpr{
	}, opt.MakeColSet(rc[1]))
	innerUnion := e.f.ConstructUnionAll(leftInner, rightInner, &memo.SetPrivate{
		LeftCols: opt.ColList{lc[1]}, RightCols: opt.ColList{rc[1]}, OutCols: opt.ColList{unionValue},
	})
	want := e.f.ConstructProject(innerUnion, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTrue(), unionConstant),
	}, opt.MakeColSet(unionValue))
	e.expectRewrite(rule, before, want)

	nearRight := e.f.ConstructProject(rightScan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructFalse(), rightConstant),
	}, opt.MakeColSet(rc[1]))
	nearMiss := e.f.ConstructUnionAll(left, nearRight, &memo.SetPrivate{
		LeftCols: opt.ColList{leftConstant, lc[1]}, RightCols: opt.ColList{rightConstant, rc[1]}, OutCols: opt.ColList{unionConstant, unionValue},
	})
	e.expectNoMatch(rule, nearMiss)
}
