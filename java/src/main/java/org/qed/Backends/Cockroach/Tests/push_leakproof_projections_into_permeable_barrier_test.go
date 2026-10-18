package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPushLeakproofProjectionsIntoPermeableBarrier(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushLeakproofProjectionsIntoPermeableBarrier")
	input, cols := e.scan("t", 1)
	barrierCol := cols[0]
	barrier := e.f.ConstructProject(input, memo.ProjectionsExpr{}, opt.MakeColSet(barrierCol))
	projectionCol := e.f.Metadata().AddColumn("projection", types.Int)
	before := e.f.ConstructProject(barrier,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(barrierCol), e.num(1)), projectionCol)}, opt.MakeColSet(barrierCol),
	)

	pushed := e.f.ConstructProject(input,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), projectionCol)}, opt.MakeColSet(cols[0]),
	)
	want := e.f.ConstructProject(pushed, memo.ProjectionsExpr{}, opt.MakeColSet(projectionCol, cols[0]))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearInput, nearCols := e.scan("t", 1)
	nearBarrierCol := nearCols[0]
	nearBarrier := e.f.ConstructProject(nearInput, memo.ProjectionsExpr{}, opt.MakeColSet(nearBarrierCol))
	nearProjectionCol := e.f.Metadata().AddColumn("projection", types.Int)
	nearMiss := e.f.ConstructProject(nearBarrier,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(nearBarrierCol), e.num(1)), nearProjectionCol)}, opt.ColSet{},
	)
	e.expectNoMatch(rule, nearMiss)
}
