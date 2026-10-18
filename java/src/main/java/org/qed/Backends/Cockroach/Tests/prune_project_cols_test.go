package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneProjectCols(t *testing.T) {
	rule := loadRule(t, "PruneProjectCols")
	e := newEnv(t)
	input, cols := e.scan("t", 3)
	used := e.f.Metadata().AddColumn("used", types.Int)
	unused := e.f.Metadata().AddColumn("unused", types.Int)
	outer := e.f.Metadata().AddColumn("outer", types.Int)
	beforeInner := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(10), used),
		e.f.ConstructProjectionsItem(e.num(20), unused),
	}, opt.MakeColSet(cols[0]))
	before := e.f.ConstructProject(beforeInner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(used)), outer),
	}, opt.ColSet{})

	wantOuter := e.f.Metadata().AddColumn("outer", types.Int)
	want := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(10)), wantOuter),
	}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearInput, nearCols := e.scan("t", 3)
	nearUsed := e.f.Metadata().AddColumn("used", types.Int)
	nearUnused := e.f.Metadata().AddColumn("unused", types.Int)
	nearOuter := e.f.Metadata().AddColumn("outer", types.Int)
	nearInner := e.f.ConstructProject(nearInput, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(10), nearUsed),
		e.f.ConstructProjectionsItem(e.num(20), nearUnused),
	}, opt.MakeColSet(nearCols[0], nearCols[1]))
	near := e.f.ConstructProject(nearInner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(nearCols[0]), e.v(nearUsed)), nearOuter),
	}, opt.ColSet{})
	e.expectNoMatch(rule, near)
}
