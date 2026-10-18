package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestConvertToLocalRelation(t *testing.T) {
	rule := loadRule(t, "ConvertToLocalRelation")
	e := newEnv(t)
	c0 := e.f.Metadata().AddColumn("c0", types.Int)
	c1 := e.f.Metadata().AddColumn("c1", types.Int)
	c2 := e.f.Metadata().AddColumn("c2", types.Int)
	values := e.intValues([]opt.ColumnID{c0, c1, c2}, []int{1, 2, 3}, []int{4, 5, 6})
	before := e.f.ConstructProject(values, nil, opt.MakeColSet(c2, c0))
	want := e.intValues([]opt.ColumnID{c2, c0}, []int{3, 1}, []int{6, 4})
	e.expectRewrite(rule, before, want)

	s, sc := e.scan("t", 3)
	e.expectNoMatch(rule, e.f.ConstructProject(s, nil, opt.MakeColSet(sc[2], sc[0])))

	d0 := e.f.Metadata().AddColumn("d0", types.Int)
	d1 := e.f.Metadata().AddColumn("d1", types.Int)
	d2 := e.f.Metadata().AddColumn("d2", types.Int)
	other := e.intValues([]opt.ColumnID{d0, d1, d2}, []int{7, 8, 9}, []int{10, 11, 12})
	e.expectNoMatch(rule, e.f.ConstructProject(other, nil, opt.MakeColSet(d2, d0)))
}
