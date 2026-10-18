package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushSelectIntoInlinableProject(t *testing.T) {
	rule := loadRule(t, "PushSelectIntoInlinableProject")
	e := newEnv(t)
	scan, cols := e.scan("t", 2)
	fCol := e.f.Metadata().AddColumn("f", e.f.Metadata().ColumnMeta(cols[0]).Type)
	gCol := e.f.Metadata().AddColumn("g", e.f.Metadata().ColumnMeta(cols[1]).Type)
	fExpr := e.f.ConstructPlus(e.v(cols[0]), e.num(1))
	gExpr := e.f.ConstructMinus(e.v(cols[1]), e.num(1))
	project := e.f.ConstructProject(scan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(fExpr, fCol),
		e.f.ConstructProjectionsItem(gExpr, gCol),
	}, opt.MakeColSet(cols...))
	p := e.f.ConstructIs(e.v(fCol), e.num(7))
	q := e.f.ConstructIs(e.v(gCol), e.num(9))
	before := e.f.ConstructSelect(project, e.filters(p, q))
	wantInput := e.f.ConstructSelect(scan, e.filters(
		e.f.ConstructIs(fExpr, e.num(7)),
		e.f.ConstructIs(gExpr, e.num(9)),
	))
	want := e.f.ConstructProject(wantInput, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(fExpr, fCol),
		e.f.ConstructProjectionsItem(gExpr, gCol),
	}, opt.MakeColSet(cols...))
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructSelect(project, e.filters(
		p,
		e.f.ConstructIs(e.v(cols[1]), e.num(9)),
	))
	e.expectNoMatch(rule, nearMiss)
}
