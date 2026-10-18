package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectCalcMerge(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectCalcMerge")
	input, cols := e.scan("t", 2)
	x, y := e.v(cols[0]), e.v(cols[1])
	cond := e.f.ConstructEq(x, y)
	filtered := e.f.ConstructSelect(input, e.filters(cond))
	e0 := e.f.ConstructProjectionsItem(e.f.ConstructPlus(x, y), e.f.Metadata().AddColumn("e0", types.Int))
	e1 := e.f.ConstructProjectionsItem(e.f.ConstructMinus(x, y), e.f.Metadata().AddColumn("e1", types.Int))
	bottom := e.f.ConstructProject(filtered, memo.ProjectionsExpr{e0, e1}, opt.ColSet{})
	c0, c1 := e.v(e0.Col), e.v(e1.Col)
	p0 := e.f.ConstructProjectionsItem(e.f.ConstructPlus(c0, c1), e.f.Metadata().AddColumn("p0", types.Int))
	p1 := e.f.ConstructProjectionsItem(e.f.ConstructMinus(c0, c1), e.f.Metadata().AddColumn("p1", types.Int))
	before := e.f.ConstructProject(bottom, memo.ProjectionsExpr{p0, p1}, opt.ColSet{})
	want0 := e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.f.ConstructPlus(x, y), e.f.ConstructMinus(x, y)), p0.Col)
	want1 := e.f.ConstructProjectionsItem(e.f.ConstructMinus(e.f.ConstructPlus(x, y), e.f.ConstructMinus(x, y)), p1.Col)
	want := e.f.ConstructProject(filtered, memo.ProjectionsExpr{want0, want1}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	missInput, missCols := e.scan("t", 2)
	missX, missY := e.v(missCols[0]), e.v(missCols[1])
	missBottom := e.f.ConstructProject(e.f.ConstructSelect(missInput, e.filters(e.f.ConstructEq(missX, missY))), memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(missX, missY), e.f.Metadata().AddColumn("m0", types.Int)),
		e.f.ConstructProjectionsItem(e.f.ConstructMinus(missX, missY), e.f.Metadata().AddColumn("m1", types.Int)),
	}, opt.ColSet{})
	missC0, missC1 := e.v(missBottom.(*memo.ProjectExpr).Projections[0].Col), e.v(missBottom.(*memo.ProjectExpr).Projections[1].Col)
	nearMiss := e.f.ConstructProject(missBottom, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(missC0, missC1), e.f.Metadata().AddColumn("n0", types.Int)),
	}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}

