package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestExtractPythonUDFs(t *testing.T) {
	rule := loadRule(t, "ExtractPythonUDFs")
	e := newEnv(t)
	r, rc := e.scan("t", 1)
	x := rc[0]
	fc := e.f.Metadata().AddColumn("f", types.Int)
	gc := e.f.Metadata().AddColumn("g", types.Int)
	before := e.f.ConstructProject(r, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(x), e.num(1)), fc),
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(x), e.num(2)), gc),
	}, opt.ColSet{})
	extract := e.f.ConstructProject(r, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(x), e.num(1)), fc),
	}, opt.MakeColSet(x))
	after := e.f.ConstructProject(extract, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(x), e.num(2)), gc),
	}, opt.MakeColSet(fc))
	e.expectRewrite(rule, before, after)
	e.expectNoMatch(rule, extract)

	r2, rc2 := e.scan("t", 2)
	a, b := rc2[0], rc2[1]
	fc2 := e.f.Metadata().AddColumn("f2", types.Int)
	gc2 := e.f.Metadata().AddColumn("g2", types.Int)
	twoColumns := e.f.ConstructProject(r2, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(a), e.num(1)), fc2),
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(b), e.num(2)), gc2),
	}, opt.ColSet{})
	e.expectNoMatch(rule, twoColumns)
}
