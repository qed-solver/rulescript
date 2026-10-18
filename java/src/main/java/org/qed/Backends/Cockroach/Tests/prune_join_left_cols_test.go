package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneJoinLeftCols(t *testing.T) {
	rule := loadRule(t, "PruneJoinLeftCols")
	e := newEnv(t)
	left, leftCols := e.scan("nn", 2)
	right, rightCols := e.scan("nn", 1)
	leftKey, rightKey := e.v(leftCols[0]), e.v(rightCols[0])
	join := e.f.ConstructInnerJoin(
		left,
		right,
		e.filters(e.f.ConstructEq(leftKey, rightKey)),
		&memo.JoinPrivate{},
	)
	resultCol := e.f.Metadata().AddColumn("result", types.Int)
	projection := e.f.ConstructPlus(leftKey, rightKey)
	before := e.f.ConstructProject(
		join,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(projection, resultCol)},
		opt.ColSet{},
	)
	prunedLeft := e.f.ConstructProject(left, memo.EmptyProjectionsExpr, opt.MakeColSet(leftCols[0]))
	wantJoin := e.f.ConstructInnerJoin(
		prunedLeft,
		right,
		e.filters(e.f.ConstructEq(leftKey, rightKey)),
		&memo.JoinPrivate{},
	)
	want := e.f.ConstructProject(
		wantJoin,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(projection, resultCol)},
		opt.ColSet{},
	)
	e.expectRewrite(rule, before, want)

	badLeft, badLeftCols := e.scan("nn", 2)
	badRight, badRightCols := e.scan("nn", 1)
	badJoin := e.f.ConstructInnerJoin(
		badLeft,
		badRight,
		e.filters(e.f.ConstructEq(e.v(badLeftCols[1]), e.v(badRightCols[0]))),
		&memo.JoinPrivate{},
	)
	badPlan := e.f.ConstructProject(
		badJoin,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(
			e.f.ConstructPlus(e.v(badLeftCols[0]), e.v(badRightCols[0])),
			e.f.Metadata().AddColumn("result", types.Int),
		)},
		opt.ColSet{},
	)
	e.expectNoMatch(rule, badPlan)
}
