package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestProjectRemove(t *testing.T) {
	rule := loadRule(t, "ProjectRemove")
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	before := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0]))
	e.expectRewrite(rule, before, s)

	e.expectNoMatch(rule, e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0], cols[1])))

	extra := e.f.Metadata().AddColumn("p", types.Int)
	computed := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), extra),
	}, opt.ColSet{})
	e.expectNoMatch(rule, computed)

	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(cols[0]), e.num(0)))))
}
