package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectSetOpTranspose(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectSetOpTranspose")
	project := func(col opt.ColumnID) opt.ScalarExpr {
		return e.f.ConstructConcat(e.v(col), e.f.ConstructConstVal(tree.NewDString("!"), types.String))
	}
	makeUnion := func() (memo.RelExpr, memo.RelExpr, memo.RelExpr, opt.ColumnID, opt.ColumnID, opt.ColumnID) {
		left, leftCols := e.scan("u", 1)
		right, rightCols := e.scan("t", 1)
		out := e.f.Metadata().AddColumn("union", types.Int)
		set := &memo.SetPrivate{LeftCols: opt.ColList{leftCols[0]}, RightCols: opt.ColList{rightCols[0]}, OutCols: opt.ColList{out}}
		union := e.f.ConstructUnionAll(left, right, set)
		return left, right, union, leftCols[0], rightCols[0], out
	}
	_, _, union, _, _, unionCol := makeUnion()
	topCol := e.f.Metadata().AddColumn("top", types.String)
	before := e.f.ConstructProject(union, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(project(unionCol), topCol)}, opt.ColSet{})
	expectedEnv := newEnv(t)
	expectedProject := func(col opt.ColumnID) opt.ScalarExpr {
		return expectedEnv.f.ConstructConcat(expectedEnv.v(col), expectedEnv.f.ConstructConstVal(tree.NewDString("!"), types.String))
	}
	expectedLeft, expectedLeftCols := expectedEnv.scan("u", 1)
	expectedRight, expectedRightCols := expectedEnv.scan("t", 1)
	expectedUnionCol := expectedEnv.f.Metadata().AddColumn("union", types.Int)
	expectedInputUnion := expectedEnv.f.ConstructUnionAll(expectedLeft, expectedRight, &memo.SetPrivate{
		LeftCols: opt.ColList{expectedLeftCols[0]}, RightCols: opt.ColList{expectedRightCols[0]}, OutCols: opt.ColList{expectedUnionCol},
	})
	expectedTopCol := expectedEnv.f.Metadata().AddColumn("top", types.String)
	expectedEnv.f.ConstructProject(expectedInputUnion,
		memo.ProjectionsExpr{expectedEnv.f.ConstructProjectionsItem(expectedProject(expectedUnionCol), expectedTopCol)}, opt.ColSet{})
	expectedLeftProject := expectedEnv.f.ConstructProject(expectedLeft,
		memo.ProjectionsExpr{expectedEnv.f.ConstructProjectionsItem(expectedProject(expectedLeftCols[0]), expectedTopCol)}, opt.ColSet{})
	expectedRightProject := expectedEnv.f.ConstructProject(expectedRight,
		memo.ProjectionsExpr{expectedEnv.f.ConstructProjectionsItem(expectedProject(expectedRightCols[0]), expectedTopCol)}, opt.ColSet{})
	expectedResult := expectedEnv.f.Metadata().AddColumn("result", types.String)
	expectedRightFresh := expectedEnv.f.Metadata().AddColumn("right_fresh", types.String)
	want := expectedEnv.f.ConstructUnionAll(
		expectedLeftProject,
		expectedEnv.f.ConstructProject(expectedRightProject,
			memo.ProjectionsExpr{expectedEnv.f.ConstructProjectionsItem(expectedEnv.v(expectedTopCol), expectedRightFresh)}, opt.ColSet{}),
		&memo.SetPrivate{LeftCols: opt.ColList{expectedTopCol}, RightCols: opt.ColList{expectedRightFresh}, OutCols: opt.ColList{expectedResult}},
	)
	want = expectedEnv.f.ConstructProject(want,
		memo.ProjectionsExpr{expectedEnv.f.ConstructProjectionsItem(expectedEnv.v(expectedResult), expectedTopCol)}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	left2, right2, _, leftCol2, rightCol2, unionCol2 := makeUnion()
	distinct := e.f.ConstructUnion(left2, right2, &memo.SetPrivate{
		LeftCols: opt.ColList{leftCol2}, RightCols: opt.ColList{rightCol2}, OutCols: opt.ColList{unionCol2},
	})
	nearMiss := e.f.ConstructProject(distinct,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(project(unionCol2), e.f.Metadata().AddColumn("distinct_top", types.String))}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}
