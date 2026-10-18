package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneJoinRightCols(t *testing.T) {
	rule := loadRule(t, "PruneJoinRightCols")
	e := newEnv(t)
	left, leftCols := e.scan("u", 1)
	right, rightCols := e.scan("t", 2)
	join := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))), &memo.JoinPrivate{})
	beforeOut := e.f.Metadata().AddColumn("out", types.Int)
	before := e.f.ConstructProject(join, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(leftCols[0]), e.v(rightCols[0])), beforeOut)}, opt.ColSet{})

	rightOnlyKey := e.f.ConstructProject(right, memo.ProjectionsExpr{}, opt.MakeColSet(rightCols[0]))
	joined := e.f.ConstructInnerJoin(left, rightOnlyKey, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))), &memo.JoinPrivate{})
	afterOut := e.f.Metadata().AddColumn("out", types.Int)
	want := e.f.ConstructProject(joined, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(leftCols[0]), e.v(rightCols[0])), afterOut)}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearMiss, nearMissCols := e.scan("t", 2)
	badJoin := e.f.ConstructLeftJoin(left, nearMiss, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(nearMissCols[0]))), &memo.JoinPrivate{})
	badOut := e.f.Metadata().AddColumn("out", types.Int)
	badPlan := e.f.ConstructProject(badJoin, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(leftCols[0]), e.v(nearMissCols[0])), badOut)}, opt.ColSet{})
	e.expectNoMatch(rule, badPlan)
}
