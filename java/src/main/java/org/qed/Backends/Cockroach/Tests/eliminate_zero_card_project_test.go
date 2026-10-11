package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateZeroCardProject(t *testing.T) {
	rule := loadRule(t, "EliminateZeroCardProject")
	e := newEnv(t)
	c0 := e.f.Metadata().AddColumn("c0", types.Int)
	c1 := e.f.Metadata().AddColumn("c1", types.Int)
	out := e.f.Metadata().AddColumn("out", types.Int)
	input := e.empty(c0, c1)
	before := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(c0), e.num(1)), out),
	}, opt.MakeColSet(c0, c1))
	want := e.empty(c0, c1, out)
	e.expectRewrite(rule, before, want)

	scan, sc := e.scan("u", 2)
	e.expectNoMatch(rule, e.f.ConstructProject(scan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.num(1)), out),
	}, opt.MakeColSet(sc...)))
	e.expectNoMatch(rule, e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(c0), e.num(1)), out),
	}, opt.MakeColSet(c0)))
	e.expectNoMatch(rule, input)
}

func TestSQLEliminateZeroCardProject(t *testing.T) {
	expectSQLFiresWith(t, "EliminateZeroCardProject",
		"SELECT x, y, x + 1 FROM u WHERE false",
		sqlHelpers{names: []string{"SimplifyZeroCardinalityGroup", "PruneValuesCols", "PruneScanCols"}})
}
