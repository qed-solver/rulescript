package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneWithCols(t *testing.T) {
	rule := loadRule(t, "PruneWithCols")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	z := e.f.Metadata().AddColumn("z", types.Int)
	with := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0]))
	expr := e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(cols[0])), z)
	before := e.f.ConstructProject(with, memo.ProjectionsExpr{expr}, opt.MakeColSet(cols[0]))
	below := e.f.ConstructProject(s, memo.ProjectionsExpr{expr}, opt.MakeColSet(cols[0]))
	want := e.f.ConstructProject(below, nil, opt.MakeColSet(z, cols[0]))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(with, memo.ProjectionsExpr{expr}, opt.ColSet{}))
}
