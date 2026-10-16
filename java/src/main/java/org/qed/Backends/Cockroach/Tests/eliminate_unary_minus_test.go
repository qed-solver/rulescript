package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateUnaryMinus(t *testing.T) {
	rule := loadRule(t, "EliminateUnaryMinus")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	z := e.f.Metadata().AddColumn("z", types.Int)
	twice := e.f.ConstructUnaryMinus(e.f.ConstructUnaryMinus(e.v(cols[0])))
	before := e.f.ConstructProject(s, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(twice, z)}, opt.ColSet{})
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(cols[0]), z)}, opt.ColSet{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(cols[0])), z),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.f.ConstructPlus(e.v(cols[0]), e.num(1))), z),
	}, opt.ColSet{}))
}
