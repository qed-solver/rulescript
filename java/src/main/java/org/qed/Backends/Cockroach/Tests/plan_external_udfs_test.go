package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPlanExternalUDFs(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PlanExternalUDFs")
	scan, cols := e.scan("t", 1)
	x := e.v(cols[0])
	fExpr := e.f.ConstructPlus(x, e.num(1))
	gExpr := e.f.ConstructMinus(x, e.num(1))
	fCol := e.f.Metadata().AddColumn("f", types.Int)
	gCol := e.f.Metadata().AddColumn("g", types.Int)
	before := e.f.ConstructProject(scan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(fExpr, fCol),
		e.f.ConstructProjectionsItem(gExpr, gCol),
	}, opt.ColSet{})

	wantEval := e.f.ConstructProject(scan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(fExpr, fCol),
	}, opt.MakeColSet(cols[0]))
	want := e.f.ConstructProject(wantEval, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMinus(e.v(cols[0]), e.num(1)), gCol),
	}, opt.MakeColSet(fCol))
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructProject(scan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(fExpr, fCol),
	}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}
