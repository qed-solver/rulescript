package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectFilterTranspose(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectFilterTranspose")
	input, cols := e.scan("t", 2)
	x, y := e.v(cols[0]), e.v(cols[1])
	eExpr := e.f.ConstructPlus(x, y)
	fExpr := e.f.ConstructMinus(x, e.num(1))
	filtered := e.f.ConstructSelect(input, e.filters(e.f.ConstructEq(eExpr, y)))
	eCol := e.f.Metadata().AddColumn("e", types.Int)
	fCol := e.f.Metadata().AddColumn("f", types.Int)
	before := e.f.ConstructProject(filtered, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(eExpr, eCol),
		e.f.ConstructProjectionsItem(fExpr, fCol),
	}, opt.ColSet{})

	lowered := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(eExpr, eCol),
		e.f.ConstructProjectionsItem(fExpr, fCol),
	}, opt.MakeColSet(cols[0], cols[1]))
	cond := e.f.ConstructEq(e.f.ConstructVariable(eCol), e.f.ConstructVariable(cols[1]))
	wantFiltered := e.f.ConstructSelect(lowered, e.filters(cond))
	want := e.f.ConstructProject(wantFiltered, memo.EmptyProjectionsExpr, opt.MakeColSet(eCol, fCol))
	e.expectRewrite(rule, before, want)

	missInput, missCols := e.scan("t", 2)
	missX, missY := e.v(missCols[0]), e.v(missCols[1])
	missExpr := e.f.ConstructPlus(missX, missY)
	missFiltered := e.f.ConstructSelect(missInput, e.filters(e.f.ConstructEq(missExpr, missX)))
	nearMiss := e.f.ConstructProject(missFiltered, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(missExpr, e.f.Metadata().AddColumn("missE", types.Int)),
		e.f.ConstructProjectionsItem(e.f.ConstructMinus(missX, e.num(1)), e.f.Metadata().AddColumn("missF", types.Int)),
	}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}
